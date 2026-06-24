import sys
p=sys.argv[1]
s=open(p,'r',encoding='utf-8').read()
print('open_braces', s.count('{'))
print('close_braces', s.count('}'))
stack=[]
for i,line in enumerate(s.splitlines(),1):
    for ch in line:
        if ch=='{': stack.append(i)
        elif ch=='}':
            if stack:
                stack.pop()
            else:
                print('unmatched closing brace at line', i)
                sys.exit(0)
if stack:
    print('unmatched opening braces at lines (top is last):', stack)
else:
    print('all braces matched')
